(ns almanac.test.storage
  (:require [almanac.storage :as storage]
            [almanac.system :as system]
            [almanac.cache :as kvstore]
            ;[lobos.core]
            ;[korma.core :refer [select aggregate]]
            )
  (:use [clojure.test]))

;; (use-fixtures :each (fn [f]
;;                       (lobos.core/migrate)
;;                       (f)
;;                       (lobos.core/rollback)))

;; (def test-info
;;   {:services ["twitter"]
;;    :socialProfiles
;;    {"twitter" [{:username "t" :url "u"}]}
;;    :photos
;;    {"twitter" [{:url "pu"}]}})

;; (def update-info
;;   {:services ["google"]
;;    :socialProfiles
;;    {"google" [{:username "t" :url "u"}]}
;;    :photos
;;    {"google" [{:url "pu"}]}})

;; (def merge-info
;;   {:socialProfiles
;;    {"twitter" [{:username "t" :url "u"}]}
;;    :photos
;;    {"twitter" [{:url "pu2"}]}})

;; (def existing-test-email "test@test.com")

;; (defn count-entities [entity]
;;   (-> entity
;;       (select (aggregate (count :*) :cnt))
;;       (first)
;;       (:cnt)))

;; (deftest storage
;;   (testing "Loading a simple profile"
;;     (is (nil? (storage/get-info existing-test-email)))

;;     (storage/store-info existing-test-email test-info)
;;     (is (= 1
;;            (count-entities storage/person)))
;;     (is (= 1
;;            (count-entities storage/photo)))
;;     (is (= 1
;;            (count-entities storage/profile)))

;;     (is (= (storage/get-info existing-test-email)
;;            test-info)))
;;   (testing "Updating profile"
;;     (storage/store-info existing-test-email test-info)
;;     (storage/store-info existing-test-email update-info)
;;     (is (= (storage/get-info existing-test-email)
;;            update-info)))
;;   (testing "Merging profile"))


(defn- create-activity-item [network user-id from]
  {:from from
   :recipients (set (vector user-id))
   :network-type network})

(defn basic-test-storage [sys]
    (let [{:keys [credentials-storage activity-storage]} sys
          st activity-storage
          net :twitter
          user-id "tester"
          comp-id "sender"
          test-creds {:access-token "token"}
          test-items1 [(create-activity-item net user-id comp-id)]
          test-items2 [(create-activity-item net user-id "miss")]]
      (is (= nil (kvstore/get-credentials credentials-storage net user-id)))

      (kvstore/set-credentials credentials-storage net user-id test-creds)
      (is (= test-creds (kvstore/get-credentials credentials-storage net user-id)))

      (is (= 0 (count (storage/get-items-for-user st user-id net))))
      (storage/add-items st test-items1)
      ;; simple test
      (is (= 1 (count (storage/get-items st))))
      (storage/add-items st test-items2)
      ;; concatenation should work
      (is (= (concat test-items1 test-items2)
             (storage/get-items st)))
      ;; filtering by conversation should work
      (is (= test-items1
             (storage/get-conversation-items st user-id comp-id net)))
      ;; filtering by user should work
      (is (= (concat test-items1 test-items2)
             (storage/get-items-for-user st user-id net)))))

(deftest social-storage
  (testing "Social mem storage implementations"
    (let [test-system (system/dev-system)]
      (basic-test-storage test-system))))
