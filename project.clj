(defproject chat-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [ring/ring-core "1.1.6"]
                 [compojure "1.0.2"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-time "0.8.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [buddy/buddy-hashers "0.6.0"]
                 [ring/ring-json "0.4.0"]



                 ]
  :main chat-server.core)
