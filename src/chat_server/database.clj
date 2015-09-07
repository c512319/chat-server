(ns chat-server.database
  (:require [chat-server.routes.websocket :refer [users chatrooms next-id msgs-world msgs-chatroom msgs-p2p ]]
            [clj-time.core :as t])
  (:use [chat-server.models]))

;;;;;;;;;;;;;;;;;;;; dummy in-memory database start ;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-users-data [id name encrypted-password talked-persons-history]
  (swap! users conj (->User id name encrypted-password talked-persons-history)))

(defn set-msg [msgs-location from to text time should-reserved]
  (swap! msgs-location conj (->Message from to text time should-reserved) ))

(defn set-chatroom [chatroom-id chatroom-owner chatroom-guests]
  (swap! chatrooms conj {:chatroom-id chatroom-id :chatroom-owner chatroom-owner :chatroom-guests chatroom-guests}))

;;;simulated initial data
(defn set-data []

  (set-users-data (next-id) "a" "1234567" ["b" "c"])
  (set-users-data (next-id) "b" "1234567" [])
  (set-users-data (next-id) "c" "1234567" ["a"])

  (set-msg msgs-world "a" :world "i am a" (t/now) true)
  (set-msg msgs-world "b" :world "i am b" (t/plus (t/now) (t/minutes 1)) true)
  (set-msg msgs-world "c" :world "i am c" (t/date-time 2015 9 5) true)

  (set-chatroom "1" "a" ["b"])
  (set-chatroom "2" "a" nil)
  (set-chatroom "3" "b" ["a" "c"]))



;;;;;;;;;;;;;;;;;;;; dummy in-memory database end   ;;;;;;;;;;;;;;;;;;;;;;;;
