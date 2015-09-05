(ns chat-server.handler
  (:require [compojure.core :refer [routes]]
            [chat-server.routes.home :refer [home-routes]]
            [chat-server.routes.websocket :refer [websocket-routes api-routes]]))

(def app
  (routes  websocket-routes api-routes home-routes))