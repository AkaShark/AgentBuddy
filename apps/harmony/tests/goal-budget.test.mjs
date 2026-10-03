import test from 'node:test';
import assert from 'node:assert/strict';
import { parseGoalBudget } from '../entry/src/main/ets/state/GoalBudget.ts';

test('optional goal budgets retain i64 precision rather than rounding through a JS number', () => {
  assert.equal(parseGoalBudget(' '), undefined);
  assert.equal(parseGoalBudget(' 10000 '), 10000n);
  assert.equal(parseGoalBudget('9007199254740993'), 9007199254740993n);
  assert.equal(parseGoalBudget('9223372036854775807'), 9223372036854775807n);
});

test('invalid and overflowing goal budgets never reach the native boundary', () => {
  for (const input of ['0', '-1', '1.5', '1e6', 'Infinity', 'NaN', '9223372036854775808']) {
    assert.throws(() => parseGoalBudget(input));
  }
});
