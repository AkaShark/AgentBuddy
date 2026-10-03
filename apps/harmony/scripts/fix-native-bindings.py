#!/usr/bin/env python3
"""Compatibility fixes for pinned uniffi-bindgen-arkts beta.2, not a second API surface.

napi-ohos accepts JS numbers as f64; UniFFI still receives f32 through the
generator's existing `as f32` conversion. Leave all extern C declarations alone.
The beta generator rejects synchronous callbacks from Rust worker threads. SSH
uses those callbacks for encrypted credentials and host trust. Dispatch them to
the owning ArkTS thread, waiting only on the native worker (never on the UI).
"""
import re
import sys
from pathlib import Path

native = Path(sys.argv[1])
source = native / "src/lib.rs"
text = source.read_text()
text = re.sub(r"pub fn [^\n]+", lambda match: re.sub(r": f32\b", ": f64", match[0]), text)
owned_return = '''// AgentBuddy: detach JS-owned return buffers before crossing to a worker.
trait OwnedCallbackReturn: Send { fn into_owned(self) -> Self; }
impl OwnedCallbackReturn for () { fn into_owned(self) -> Self { self } }
impl OwnedCallbackReturn for napi_ohos::bindgen_prelude::Uint8Array {
    fn into_owned(self) -> Self { Self::from(self.as_ref().to_vec()) }
}

'''
if owned_return not in text:
    assert 'struct CallbackFunction<Args, Return>' in text
    text = text.replace('struct CallbackFunction<Args, Return>', owned_return + 'struct CallbackFunction<Args, Return>', 1)
text = text.replace('fn call_blocking(&self, args: Args) -> Result<Return> {',
                    'fn call_blocking(&self, args: Args) -> Result<Return> where Return: OwnedCallbackReturn {')
old_callback = '''        let _ = args;
        Err(Error::new(
            Status::GenericFailure,
            "synchronous UniFFI callback was invoked from a different thread; ArkTS cannot synchronously dispatch this callback without blocking the JS thread",
        ))'''
new_callback = '''        // AgentBuddy: call_direct above handles UI-thread re-entry. Only a
        // native worker waits here; ArkTS remains free to service the callback.
        let (sender, receiver) = std::sync::mpsc::sync_channel(1);
        self.call_with_return_callback(args, move |result, _env| {
            let result = result.map(OwnedCallbackReturn::into_owned)
                .map_err(|error| Error::new(error.status, error.reason.clone()));
            let _ = sender.send(result);
            Ok(())
        })?;
        receiver.recv_timeout(std::time::Duration::from_secs(30)).map_err(|error|
            Error::new(Status::GenericFailure, format!("UniFFI callback did not finish: {error}"))
        )?'''
if old_callback in text:
    text = text.replace(old_callback, new_callback, 1)
elif new_callback not in text:
    raise SystemExit("Generator callback implementation changed; review the compatibility fix.")
source.write_text(text)
manifest = native / "Cargo.toml"
manifest.write_text(manifest.read_text().replace('uniffi = "0.31.0"', 'uniffi = "=0.31.0"'))
