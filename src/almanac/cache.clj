(ns almanac.cache
  (:gen-class))

(defprotocol KeyValueStore
  (get-value [store key])
  (set-value [store key value]))

(defn mem-store []
  (let [store (atom {})]
    (reify KeyValueStore
      (get-value [_ key]
        (get @store key))
      (set-value [_ key value]
        (swap! store assoc key value)))))


(defn- credentials-key [user-id network]
  (format "%s@%s-cr" user-id (name network)))

(defn get-credentials [store network user-id]
  (get-value store (credentials-key user-id network)))

(defn set-credentials [store network user-id credentials]
  (set-value store (credentials-key user-id network) credentials))
