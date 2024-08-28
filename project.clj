(defproject org.clojars.jansuran03/task-scheduler "1.0.1"
  :description "A Clojure library designed for asynchronous scheduling of tasks."
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]]
  :aliases {"test" ["run" "-m" "task-scheduler.core-test/-main"]}
  :profiles {:dev  {:repl-options {:init-ns jansuran03.task-scheduler.core}}
             :test {:repl-options {:init-ns task-scheduler.core-test}}}
  :repositories [["clojars" {:url   "https://repo.clojars.org"
                             :creds :gpg}]])

