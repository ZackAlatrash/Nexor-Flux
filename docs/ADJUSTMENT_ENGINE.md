# Adjustment Engine

The adjustment engine is deterministic pure Kotlin in `domain/adjustment`.

## Inputs
- Logged days
- Adherence percent
- Weeks since maintenance phase start
- Weight trend in kg/week
- Waist trend in cm/week
- Performance trend
- Recovery trend

## Verdicts
- `WAIT_FOR_DATA`
- `HOLD`
- `INCREASE_CALORIES`
- `REDUCE_CALORIES`

## Rules
- Fewer than 14 usable days waits for more data.
- Adherence below the configured minimum waits for better logging consistency.
- First-week scale jump with stable waist holds.
- Stable weight, stable waist, and stable/up performance holds.
- Falling weight with poor recovery or down performance increases calories.
- Rising weight with rising waist reduces calories.
- Weight up, waist stable, and performance up holds.

Core cases are covered in `AdjustmentEngineTest`.
