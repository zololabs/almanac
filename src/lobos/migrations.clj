(ns lobos.migrations
  (:refer-clojure :exclude [alter drop
                            bigint boolean char double float time])
  (:use (lobos [migration :only [defmigration]] core schema config)))

(defmigration initial
  (up []
      (create (table :person
                     (integer :id :unique :auto-inc :primary-key)
                     (varchar :email 100 :unique)
                     (timestamp :last_updated_at (default (now)))))
      (create (table :profile
                     (integer :id :unique :auto-inc :primary-key)
                     (varchar :service 100)
                     (varchar :username 100)
                     (varchar :url 1024)
                     (integer :person_id [:refer :person :id])))
      (create (table :photo
                     (integer :id :unique :auto-inc :primary-key)
                     (varchar :service 100)
                     (varchar :url 1024)
                     (integer :person_id [:refer :person :id]))))
  (down []
        (drop (table :photo))
        (drop (table :profile))
        (drop (table :person))))
