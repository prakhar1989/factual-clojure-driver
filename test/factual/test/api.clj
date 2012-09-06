(ns factual.test.api
  (:require [factual.api :as fact])
  (:import [factual.api factual-error])
  (:use [clojure.test]
        [clojure.data.json :only (json-str read-json)]
        [slingshot.slingshot]))

(defn connect
  "Test fixture that connects this namespace to Factual's API.
   You must put your key and secret in resources/oauth.json.
   See resources/oauth.sample.json for the expected format."
  [f]
  (let [auth (read-json (slurp "resources/oauth.json"))]
    (fact/factual! (:key auth) (:secret auth)))
  (f))

(use-fixtures :once connect)

(deftest test-fetch-random-sample
  (is (< 10 (count (fact/fetch {:table :places})))))

(deftest test-fetch-filters
  (let [res (fact/fetch {:table :restaurants-us :filters {:name "Starbucks"}})
        uniq-names (vec (distinct (map :name res)))]
    (is (= ["Starbucks"] uniq-names))))

(deftest test-fetch-factual-error
  (try+
    (fact/fetch {:table :places
                :filters {:factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :BAD :PARAM}})
    (catch factual-error {code :code message :message opts :opts}
     (is (not (nil? code)))
     (is (not (nil? message)))
     (is (not (nil? opts)))
     (is (= (get-in opts [:filters :BAD]) :PARAM)))))

(deftest test-fetch-nearby-cafes
  "Returns up to 50 cafes within specified miles of specified location."
  []
  (let [res (fact/fetch {:table :places
              :q "cafe"
              :filters {:category {:$eq "Food & Beverage"}}
              :geo {:$circle {:$center [34.06018 -118.41835]
                              :$meters (* 3 1609.344)}}
              :include_count true
                        :limit 5})]
    (is (= 5 (count res)))
    (is (< 5 (get-in (meta res) [:response :total_row_count])))))

(deftest test-resolve
  (let [res (first
             (fact/resolve {:name "taco"
                           :address "10250 santa monica"}))]
    (is (= true (:resolved res)))))

(deftest test-match
  (let [res (fact/match {:name "McDonalds" :latitude 34.05671 :longitude -118.42586})]
    (is (= 1 (count res)))
    (is (contains? (first res) :factual_id))))

(deftest test-crosswalk
  (is (< 3 (count
             (fact/fetch {:table :crosswalk :filters {:factual_id "97598010-433f-4946-8fd5-4a6dd1639d77"}})))))


(deftest test-multi
  (let [res (fact/multi {:query1 {:api fact/fetch* :args [{:table :global :q "cafe" :limit 10}]}
                         :query2 {:api fact/facets* :args [{:table :global :select "locality,region" :q "http://www.starbucks.com"}]}
                         :query3 {:api fact/reverse-geocode* :args [34.06021 -118.41828]}})]
    (is (not (empty? (get-in res [:query2 :locality]))))
    (is (= 10 (count (:query1 res))))
    (is (.equals (get-in res [:query3 0 :address]) "1801 Avenue Of The Stars"))))

;;diff backend broken
#_(deftest test-diff
    (let [res (fact/diff {:table :global :start 1318890505254 :end 1318890516892})]))

;;submit backend broken
#_(deftest test-submit
  (let [res (fact/submit {:table "t7RSEV" :user "test_user" :values {:name "A New Restaurant" :locality "Los Angeles"}})]
    (is (not
         (or (nil? (get res :factual_id))
             (nil? (get res :new_entity)))))))

;;flag backend broken
#_(deftest test-flag
    (let [res (fact/flag "74ce3ae9-7ae1-4141-9752-1cac3305b797" {:table "restaurants-us" :problem "nonexistent" :user "test_user"})]
      ...))

(defn every-locality? [res val]
  (every? #(= % val) (map :locality res)))

(deftest test-unicode-basic
  (let [res (fact/fetch {:table :global
                         :filters {:locality "大阪市"}
                         :limit 5})]
    (is (= (count res) 5))
    (is (every-locality? res "大阪市"))))

(deftest test-unicode-multi
         (let [mres (fact/multi {:q1 {:api fact/fetch* :args [{:table :global :filters {:locality "בית שמש"}}]}
                                 :q2 {:api fact/fetch* :args [{:table :global :filters {:locality "München"} :limit 10}]}
                                 :q3 {:api fact/resolve* :args [{:name "César E. Chávez Library" :locality "Oakland" :region "CA" :address "3301 E 12th St"}]}})
               res1 (:q1 mres)
               res2 (:q2 mres)
               res3 (:q3 mres)]

    (is (> (count res1) 0))
    (is (every-locality? res1 "בית שמש"))

    (is (= (count res2) 10))
    (is (every-locality? res2 "München"))

    (is (= (count res3) 1))
    (is (= (:tel (first res3)) "(510) 535-5620"))))
