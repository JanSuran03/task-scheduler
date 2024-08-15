(ns task-scheduler.priority-queue
  (:require [clojure.test :refer :all]
            [jansuran03.task-scheduler.priority-queue :as pq]))

(defn heapsort [q]
  (loop [q q
         data []]
    (if-let [res (pq/extract-min q)]
      (recur (first res)
             (conj data (second res)))
      data)))

(defn test-heapsort [lt-cmp id-fn]
  (every? true? (for [size (range 1 30)
                      _ (range 5)
                      :let [random-data (take size (shuffle (range 100)))
                            from-array (pq/build (pq/create lt-cmp id-fn) random-data)
                            from-multi-insert (reduce pq/insert (pq/create lt-cmp id-fn) random-data)
                            removed (take (quot size 4) (shuffle random-data))
                            std-sorted (sort lt-cmp (remove (set removed) random-data))
                            from-array-removed (reduce #(first (pq/remove-by-id %1 (id-fn %2))) from-array removed)
                            from-multi-insert-removed (reduce #(first (pq/remove-by-id %1 (id-fn %2))) from-multi-insert removed)]]
                  (= (heapsort from-array-removed)
                     (heapsort from-multi-insert-removed)
                     std-sorted))))

(deftest priority-queue
  (is (test-heapsort < identity))
  (is (test-heapsort > identity))
  (is (test-heapsort < #(* % 42)))
  (is (test-heapsort > #(* % 42))))
