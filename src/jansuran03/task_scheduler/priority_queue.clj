(ns jansuran03.task-scheduler.priority-queue)

(defprotocol IPriorityQueue
  (get-min [queue]
    "Return an element `x` from the queue for which (not (lt y x)) is true
    for each other element `y` in the queue.

    Works in Θ(1).")
  (build [queue elements]
    "Build a priority queue out of the given unsorted elements, expecting
    the element at index 0 not to ever be accessed.

    Works in Θ(n).")
  (extract-min [queue]
    "Extract an element `x` from the queue for which (not (lt y x)) is true for each
    other element `y` in the queue. Returns a pair of [new-priority-queue extracted-min].

    Works in Θ(log(n)).")
  (insert [queue x]
    "Inserts a new element to the priority queue, keeping the heap ordering.

    Works in Θ(log(n))")
  (queue-empty? [queue]
    "Returns true iff the queue is empty."))

(defn- ^:no-doc bubble-down [[queue lt] index]
  (let [size (count queue)]
    (loop [cur index
           queue (transient queue)]
      (if (< (bit-shift-left cur 1) size)
        (let [child (bit-shift-left cur 1)
              child (if (and (< child (dec size))
                             (lt (queue (inc child)) (queue child)))
                      (inc child)
                      child)]
          (if (lt (queue child) (queue cur))
            (let [tmp-child (queue child)]
              (recur child (assoc! queue child (queue cur) cur tmp-child)))
            (persistent! queue)))
        (persistent! queue)))))

(defn- ^:no-doc bubble-up [[queue lt] index]
  (loop [cur index
         queue (transient queue)]
    (if (<= cur 1)
      (persistent! queue)
      (let [parent (bit-shift-right cur 1)]
        (if (lt (queue cur) (queue parent))
          (let [tmp-parent (queue parent)]
            (recur parent (assoc! queue parent (queue cur) cur tmp-parent)))
          (persistent! queue))))))

(defrecord PriorityQueue [queue lt-cmp]
  IPriorityQueue
  (get-min [this]
    (second queue))
  (build [this elements]
    (PriorityQueue. (reduce (fn [queue index]
                              (bubble-down [queue lt-cmp] index))
                            (into [::no-item] elements)
                            (range (bit-shift-right (count elements) 1) 0 -1))
                    lt-cmp))
  (extract-min [this]
    (when-let [min (second queue)]
      [(PriorityQueue. (bubble-down [(-> queue (assoc 1 (peek queue)) pop) lt-cmp] 1)
                       lt-cmp)
       min]))
  (insert [this x]
    (PriorityQueue. (bubble-up [(conj queue x) lt-cmp] (count queue))
                    lt-cmp))
  (queue-empty? [this]
    (<= (count queue) 1)))

(defn create
  "Creates a priority queue with the given comparator, where an element `x` from
  the queue for which (not (lt y x)) is true for each other element `y` in the
  queue, will be treated as the minimum by `get-min` and `extract-min`."
  [lt-comparator]
  (PriorityQueue. [::no-item] lt-comparator))
