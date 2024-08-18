(ns task-scheduler.core-test
  (:require [clojure.test :refer :all]))

(defn -main [& _]
  (let [test-nss '[task-scheduler.priority-queue
                   task-scheduler.scheduler]]
    (doseq [test-ns test-nss]
      (require test-ns))
    (let [result (apply run-tests test-nss)]
      (if (successful? result)
        (System/exit 0)
        (System/exit 1)))))
