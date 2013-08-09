(ns almanac.system
  (:require [almanac.cache :as cache]
            [almanac.storage :as ss]
            [almanac.rds-cache :as rds]))

(defprotocol Service
  (start [this options])
  (stop [this options]))

(defn- service-operation [system service-name op]
  (let [service (get system service-name)]
    (when (satisfies? Service service)
      (op service))))

(defn- start-service [system service-name options]
  (service-operation system service-name #(start % options)))

(defn- stop-service [system service-name options]
  (service-operation system service-name #(stop % options)))

(defn- system-service-keys []
  [:fullcontact-cache :credentials-storage :activity-storage :aux-cache])

(defrecord AlmanacSystem [fullcontact-cache credentials-storage activity-storage aux-cache]
  Service
  (start [this options]
    (doseq [service-name (system-service-keys)]
      (start-service this service-name (get options service-name))))
  (stop [this options]
    (doseq [service-name (system-service-keys)]
      (stop-service this service-name (get options service-name)))))

(defn dev-system []
  (->AlmanacSystem (cache/mem-store)
                   (cache/mem-store)
                   (ss/mem-storage)
                   (cache/mem-store)))

(defn prod-system []
  (->AlmanacSystem (rds/fullcontact-cache)
                   (cache/mem-store)
                   (ss/mem-storage)
                   (cache/mem-store)))
