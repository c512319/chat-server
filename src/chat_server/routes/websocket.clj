(ns chat-server.routes.websocket
  (:require [compojure.core :refer [defroutes GET]]
            [org.httpkit.server :as server]
            [taoensso.timbre :as timbre]
            [buddy.hashers :as hashers]
            [clojure.data.json :refer [json-str read-json]]
            [clj-time.core :as t]
            [org.httpkit.timer :as timer]
            [clojure.core.async :refer [chan alts! timeout go >!]])
  (:use chat-server.models))                            ;import models(record): User, Message.



;;;;;;;;;;  global varialbes  ;;;;;;;;;;



;; Alpha to change
;; 控制定期清理任务开始的分钟数,*clean-minutes*为(0 1 2 3)时表示从现在0 1 2 3 minute的时候,
;; 分别用org.httpkit.timer/schedule-task分配一次计划任务,
;; Problems:
;; 1.*clean-minutes*设置太大(start-clean-task)的时候,触发clojure.lang.RT.intCast(RT.java.1191)
;;    IllegalArgumentException Value out of range for int: 2147520000
;; 2.org.httpkit.timer.TimerService schedule太多会不会有问题
;; 3.默认计划分配了5小时的清理任务,需要其他手段定期执行start-clean-task,脚本定期执行??
;;   改用偏移式schedule??
(def ^:dynamic *clean-minutes* (range (* 5 3600)))

(def users (atom (sorted-map)))

(def chatrooms (atom (sorted-map)))

(def msgs-world (atom #{}))
(def msgs-chatroom (atom []))                               ;msgs-charroom model:[{...} ...]
(def msgs-p2p (atom #{}))

(def world-channels (atom #{}))
(def chatroom-channels (atom #{}))
(def p2p-channels (atom #{}))

(def channels-map {:p2p p2p-channels
                   :chatroom chatroom-channels
                   :world world-channels})



;;;;;;;;;;  user about  ;;;;;;;;;;



(def current-id (atom 0))
(defn next-id []
  (swap! current-id inc)
  @current-id)

(defn get-user-by-id [user-id]  ; user-id is keyword,put user-id first to prevent unknown nil
  (user-id @users))

(defn get-user-by-name [name]
  (first (filter #(= name (:name %)) @users)))

(def get-user get-user-by-name)

(defn get-encrypted-password-from-database [name]
  (:encrypted-password (get-user name)))



;;;;;;;;;;  chatroom about  ;;;;;;;;;;



(defn get-chatroom-by-id [chatroom-id]
  (chatroom-id @chatrooms))

(defn validate-chatroom [chatroom-id user-name]
  (let [chatroom (get-chatroom-by-id chatroom-id)
        owner (:chatroom-owner chatroom)
        guests (:chatroom-guests chatroom)]
    (and chatroom
         (or (= owner user-name)
             (some #(= % user-name) guests)))))

(defn invite [chatroom-id chatroom-owner chatroom-guest]
  (if (and chatroom-id chatroom-owner chatroom-guest)
    (swap! chatrooms update-in [chatroom-id :chatroom-guests] #(conj % chatroom-guest))
    (timbre/info "邀请好友信息不完全:要求chatroom-id,chatroom-owner,chatroom-guest")))



;;;;;;;;;;  msgs about   ;;;;;;;;;;



(defn to-msg-type [to]
  (cond
    (instance? String to) :p2p
    (vector? to) :chatroom
    (= :world to) :world))

(defn clean-msgs [msgs-location]
  "Clean msgs in atom msg-location,msg is instance of Message,msg-location is (atom #{...}),return the changed atom."
  (doseq [m @msgs-location]
    (if (t/after? (t/now) (t/plus (:time m) (t/minutes 5)))
      (swap! msgs-location disj m)))
  msgs-location)

(defn Message-from-map [-map]
  (map->Message {:from (:from -map)
                 :to (:to -map)
                 :text (:text -map)
                 :time (t/now)
                 :should-reserved true}))



;;;;;;;;;;  clean task  about ;;;;;;;;;;



;;简单粗暴的future-loop-sleep轮询清理
;;定时清除msgs-world,msgs-chatroom中信息,默认1分钟,
;; (defn start-clean-task
;;   ([]
;;    (start-clean-task 1))
;;   ([minutes]
;;    (future
;;      (loop []
;;        (clean-msgs msgs-world)
;;        (clean-msgs msgs-chatroom)
;;        (Thread/sleep (* minutes 60 1000))
;;        (recur)))))

;; reimplement start-clean-task,using timer/schedule-task and core.async/timeout channel.
(defn start-clean-task []
  (doseq [i *clean-minutes*]
    (let [ms (* i 60 1000)]
      (timer/schedule-task ms
                           (let [c (chan)]
                             (go (alts! [c (timeout ms)]))
                             (go (>! c (do
                                         (clean-msgs msgs-world)
                                         (clean-msgs msgs-chatroom)))))))))



;;;;;;;;;; connect and channel abount  ;;;;;;;;;;



(defn get-right-channel [to]
  (channels-map (to-msg-type to)))

(defn connect! [channel to]
  (timbre/info (str "channel: " channel "connected"))
  (swap! (get-right-channel to) conj channel))

(defn disconnect! [channel to status]
  (timbre/info (str "channel: " channel "closed,status: " status "."))
  (swap! (get-right-channel to) disj channel))



;;;;;;;;;;  request about fns ;;;;;;;;;;



;;服务器中存加密后的密码,加密算法与客户端约定,使用buddy.hashers/encrypted,bcrypt+sha512,
(defn validate-request [req]
  (= (get-encrypted-password-from-database (-> req :params :name)) (-> req :params :password)))



(defn get-latest-message-text [from to]
  (last (sort-by :time
                 (filter #(and (= from (:from %))
                               (= to   (:to %)))
                         @msgs-p2p))))

(defn get-p2p-records
  "Return p2p-records user list,and latest message,model:{:user-list xxx,:latest-messages:[[Talk-TO-Person Message-Text]] ."
  [user-id]
  (let [user (get-user-by-id user-id)
        from (:name user)
        talked-persons-history (:talked-persons-history user) ;;每个对应Message. 中的to
        latest-messages (vec (for [to talked-persons-history]
                               [to (get-latest-message-text from to)]))]
    {:user-list talked-persons-history :latest-messages latest-messages}))



;;;;;;;;;;  handler about  ;;;;;;;;;;



;;req的body是json
;;客户端确保发过来的信息 :from :to :text三个字段,
;;想进入用户创建的聊天室,需要客户端请求参数中: :chatroom 不为nil,并且validate-chatroom成功,
;;拉好友进群,请求参数: :invite 不为nil,并且:invited-guest 为要邀请的好友名字
(defn websocket-handler [req]
  (server/with-channel req channel
                       (let [msg (read-json (:body req))
                             to (:to msg)
                             msg-type (to-msg-type to)
                             chatroom-id (-> req :params :chatroom-id)
                             user-name (-> req :params :name)
                             _ (if (-> req :params :invite)
                                 (let [invited-guest (-> req :params :invited-guest)]
                                   (invite chatroom-id user-name invited-guest)))]
                         (if (and (validate-request req)
                                  (if (-> req :params :chatroom) (validate-chatroom chatroom-id user-name) true))
                           (do
                             (connect! channel to)
                             (server/on-close channel (partial disconnect! channel to))
                             (server/on-receive channel
                                                (cond
                                                  (= msg-type :world)`
                                                  (fn [msg]
                                                    (server/send! channel msg)
                                                    (swap! msgs-world conj (Message-from-map msg)))

                                                  (= msg-type :chatroom)
                                                  (fn [msg]
                                                    (server/send! channel msg)
                                                    (swap! msgs-chatroom conj (array-map chatroom-id (Message-from-map msg))))

                                                  (= msg-type :p2p)
                                                  (fn [msg]
                                                    (let [user-id (:from msg)
                                                          new-talked-person (:to msg)
                                                          user (get-user-by-id user-id)
                                                          new-talked-persons-history (if user
                                                                                       (conj (:talked-persons-history user) new-talked-person)
                                                                                       #{})
                                                          new-user (update-in user [:talked-persons-history] new-talked-persons-history)]
                                                      (server/send! channel msg)
                                                      ;;msg中的to存到users的from(User对象的)的talked-persons-history中
                                                      ;;以便请求点对点聊天过的好友列表时用.
                                                      (swap! users assoc user-id new-user)
                                                      (swap! msgs-p2p conj (Message-from-map msg)))))
                                                ))
                           (server/send! channel (json-str {:error true :reason "用户名,密码验证不正确."}))))))



;;;;;;;;;; routes abount  ;;;;;;;;;;



(defroutes websocket-routes
           (GET "/ws" req (websocket-handler req)))

;;api-routes给客户端json vector,
(defroutes api-routes
           (GET "/api/world-records" [] (json-str (deref (clean-msgs msgs-world))))
           (GET "/api/chatroom-records" [chatroom-id] (json-str (filter #(= chatroom-id (key %)) (deref (clean-msgs msgs-chatroom)))))
           (GET "/api/p2p-records" [user-id] (json-str (get-p2p-records user-id))))
