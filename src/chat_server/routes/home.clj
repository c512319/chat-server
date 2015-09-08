(ns chat-server.routes.home
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]))



(defroutes home-routes
           (GET "/" [] "<h1>Home Page<h1>")
           (route/not-found "<h1>Page not found.<h1>"))
