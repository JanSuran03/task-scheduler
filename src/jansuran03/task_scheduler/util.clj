(ns jansuran03.task-scheduler.util
  (:import (clojure.lang MultiFn)))

(def ^:private scheduler-hierarchy (make-hierarchy))

(defn local-multifn
  "Given a dispatch-function, creates and returns a non-global multifunction object,
  on which functions like `defmethod`, `methods`, etc. can be called."
  [dispatch-fn]
  (MultiFn. (name (gensym (str "local_multifn_"))) dispatch-fn :default #'scheduler-hierarchy))
