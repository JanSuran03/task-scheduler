### 1.0.0
- stable version of basic functionalities: `schedule`, `schedule-new`, `schedule-interval`, `schedule-new-interval`,
`wait-for-tasks`, `stop`, `stop-and-wait`, `cancel-schedule`

### 1.0.1
- use library-based hierarchy for local multifunctions instead of an unnecessary local var

### 1.0.2
- change `(a/alts [(a/chan) ch])` to `(a/<! ch)`