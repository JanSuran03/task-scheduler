(ns task-scheduler.core-test
  (:require [clojure.test :refer :all]))

(defn -main [& _]
  (let [test-nss '[task-scheduler.priority-queue]]
    (doseq [test-ns test-nss]
      (require test-ns))
    (apply run-tests test-nss)))
