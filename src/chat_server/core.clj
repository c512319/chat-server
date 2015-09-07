(ns chat-server.core
  (:require [org.httpkit.server :as server]
            [taoensso.timbre :as timbre]
            [chat-server.handler :refer [app]]
            [clj-time.core :as t]
            [chat-server.routes.websocket :refer [start-clean-task]]
            [chat-server.database :refer [set-data]])
  (:gen-class))

(defonce server (atom nil))

(defn parse-port [[port]]
  (Integer/parseInt (or port "8090")))

(defn start-server [port]
  (reset! server (server/run-server #'app {:port port})))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (let [port (parse-port args)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop-server))
    (start-server port)
    (start-clean-task)
    (timbre/info "server started on port:" port)
    (set-data)
    (timbre/info "simulated data seted.")
    ))
