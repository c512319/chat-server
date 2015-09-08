(ns chat-server.models)



(defrecord User [id name encrypted-password talked-persons-history])



;; to可能值: :world,:chatroom,:p2p,表示三种聊天场景.
;; should-reverved :
;; 服务器定期根据此字段清除msgs-world,msgs-chatroom中的消息,默认1分钟,
;; websocket中send!根据此字段判断要不要发给客户端时,默认5分钟,让感觉起来像消息只保存一段时间.
;;Message time字段用clj-time,t/data-time
(defrecord Message [from to text time should-reserved])



(defn dispatch-msg-fn [msg]
  (:to msg))
