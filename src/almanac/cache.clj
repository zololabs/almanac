(ns almanac.cache
  (:gen-class)
  ;(:gen-interface)
)

(defprotocol KeyValueStore
  (get-value [store key])
  (set-value [store key value]))

(defn mem-store []
  (let [store (atom {})]
    (reify KeyValueStore
      (get-value [_ key]
        (get @store key))
      (set-value [_ key value]
        (swap! @store assoc key value)))))
