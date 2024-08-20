[![Build Status](https://github.com/JanSuran03/task-scheduler/actions/workflows/clojure.yml/badge.svg)](https://github.com/JanSuran03/task-scheduler/actions)

Task-scheduler

A Clojure library designed for asynchronous scheduling of tasks

## Usage

Require the code namespace:
```clojure
(require '[jansuran03.task-scheduler.core :as scheduler])
=> nil
```

You can schedule a task to be executed after a certain millisecond timeout, while not blocking the current thread during
the preparation time.
`wait-for-tasks` allows you to wait for all scheduled tasks to finish running.
As you can see, each task also has their own ID (which will be important later):

```clojure
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule scheduler :task-1 #(println "Hello after 1 second") 1000)
  (scheduler/wait-for-tasks scheduler))
Hello after 1 second
=> true
```

You can also schedule events to happen in a loop after a certain interval. Calling `stop` will immediately terminate the scheduler
and prevent rescheduling of tasks, while letting the tasks, that already started executing, finish:
```clojure
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule-interval scheduler :task-1 #(println "Hello after 1 second") 1000)
  (Thread/sleep 2500)
  (scheduler/stop scheduler))
Hello after 1 second
Hello after 1 second
=> true
```

`wait-for-tasks` will also prevent scheduled tasks from being put back into the queue again.
```clojure
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule-interval scheduler :task-1 #(println "Hello after 1 second") 1000)
  (scheduler/wait-for-tasks scheduler))
Hello after 1 second
=> true
```

Calling `scheduler/stop-and-wait` is a combination of `stop` and `wait-for-tasks`, canceling tasks in the queue and
waiting to finish for the ones that already started executing:
```clojure
=> true
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule scheduler :task-1 #(println "Hello after 1 second") 1000)
  (scheduler/schedule scheduler :task-1 #(do (Thread/sleep 2000)
                                             (println "Hello after 2.5 seconds")) 500)
  (Thread/sleep 700)
  (scheduler/wait-for-tasks scheduler))
Hello after 2.5 seconds
=> true
```

You can have guarantees about not scheduling multiple tasks with the same ID - these functions will also check,
whether a task with that ID is scheduled, and discard it eventually. The functions without `"new"` schedule
the task no matter what, even allowing to replace an interval task with a one-time task and the other way.
```clojure
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule-new scheduler :task-1 #(println "Hello 1") 1000)
  (scheduler/schedule-new scheduler :task-1 #(println "Hello 2") 500)
  (scheduler/schedule-new-interval scheduler :task-2 #(println "Hello 3") 300)
  (scheduler/schedule-new-interval scheduler :task-2 #(println "Hello 4") 300)
  (Thread/sleep 400)
  (scheduler/wait-for-tasks scheduler))
Hello 3
Hello 3
Hello 1
=> true
```

The most important use of the IDs is canceling a task by its ID.
```clojure
(let [scheduler (scheduler/create-scheduler)]
  (scheduler/schedule-new-interval scheduler :task-2 #(println "Hello 3") 300)
  (scheduler/schedule-new-interval scheduler :task-2 #(println "Hello 4") 300)
  (Thread/sleep 400)
  (scheduler/cancel-schedule scheduler :task-2)
  (scheduler/wait-for-tasks scheduler))
Hello 3
=> true
```

All operations, which might or might not have succeeded, return a promise with the result:
```clojure
(let [scheduler (scheduler/create-scheduler)]
  [@(scheduler/cancel-schedule scheduler :foo) ; false
   @(scheduler/schedule-new scheduler :foo #(println "Foo") 100) ; true
   @(scheduler/schedule-new scheduler :foo #(println "Foo") 100) ; false
   @(scheduler/schedule-new-interval scheduler :bar #(println "Bar") 100) ; true
   @(scheduler/schedule-new-interval scheduler :bar #(println "Bar") 100) ; false
   @(scheduler/cancel-schedule scheduler :foo) ; true
   @(scheduler/cancel-schedule scheduler :bar)]) ; true
=> [false true false true false true true]
```

Additionally, you can define your own task handler which MUST NOT BLOCK!
```clojure
; default:
(scheduler/create-scheduler {:exec-fn #(clojure.core.async/go (%))})
; other examples:
(scheduler/create-scheduler {:exec-fn #(future (%))})
(let [executor (SomeExecutor/create)]
  (scheduler/create-scheduler {:exec-fn #(.execute executor %)}))
```
The default handler is ``,
other examples might be `#(future (%))`

## License

Copyright © 2024 Jan Šuráň

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
