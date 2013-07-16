(ns almanac.system
  (:require [almanac.cache :as cache]
            [almanac.storage :as ss]
            [almanac.rds-cache :as rds]))

(defrecord AlmanacSystem [fullcontact-cache credentials-storage activity-storage aux-cache])

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
