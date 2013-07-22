(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [almanac.core :as core]
            [almanac.system :refer [dev-system prod-system]]
            [clojure.repl :refer [doc]]
            [clojure.pprint :refer [pprint]]))

(def ^:dynamic sys nil)

(defn start []
  (alter-var-root (var sys) (constantly (dev-system))))

(defn stop []
  (alter-var-root (var sys) (constantly nil)))
