(ns almanac.test.storage
  (:require [almanac.storage :as storage]
            [lobos.core]
            [korma.core :refer [select aggregate]])
  (:use [clojure.test]))

(use-fixtures :each (fn [f]
                      (lobos.core/migrate)
                      (f)
                      (lobos.core/rollback)))

(def test-info
  {:services ["twitter"]
   :socialProfiles
   {"twitter" [{:username "t" :url "u"}]}
   :photos
   {"twitter" [{:url "pu"}]}})

(def update-info
  {:services ["google"]
   :socialProfiles
   {"google" [{:username "t" :url "u"}]}
   :photos
   {"google" [{:url "pu"}]}})

(def merge-info
  {:socialProfiles
   {"twitter" [{:username "t" :url "u"}]}
   :photos
   {"twitter" [{:url "pu2"}]}})

(def existing-test-email "test@test.com")

(defn count-entities [entity]
  (-> entity
      (select (aggregate (count :*) :cnt))
      (first)
      (:cnt)))

(deftest storage
  (testing "Loading a simple profile"
    (is (nil? (storage/get-info existing-test-email)))

    (storage/store-info existing-test-email test-info)
    (is (= 1
           (count-entities storage/person)))
    (is (= 1
           (count-entities storage/photo)))
    (is (= 1
           (count-entities storage/profile)))

    (is (= (storage/get-info existing-test-email)
           test-info)))
  (testing "Updating profile"
    (storage/store-info existing-test-email test-info)
    (storage/store-info existing-test-email update-info)
    (is (= (storage/get-info existing-test-email)
           update-info)))
  (testing "Merging profile"))
