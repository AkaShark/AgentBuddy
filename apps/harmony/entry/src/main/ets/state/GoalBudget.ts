// Keep full i64 precision across the UniFFI bigint boundary. Empty means the
// caller did not request a budget change, rather than a zero-token budget.
export function parseGoalBudget(text: string): bigint | undefined {
  const value = text.trim();
  if (!value) return undefined;
  if (!/^[0-9]+$/.test(value)) throw new Error('Token 预算必须为正整数。');
  const budget = BigInt(value);
  if (budget <= BigInt(0) || budget > BigInt('9223372036854775807')) throw new Error('Token 预算超出有效范围。');
  return budget;
}
