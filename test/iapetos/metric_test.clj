(ns iapetos.metric-test
  (:require [clojure.test :refer :all]
            [iapetos.metric :as metric]))

(deftest t-metric-name
  (are [value expected-name expected-namespace]
       (= [expected-name expected-namespace]
          ((juxt :name :namespace) (metric/metric-name value)))
       "my-metric"         "my_metric" nil
       {:name "my-metric"} "my_metric" nil
       ["app" "my-metric"] "my_metric" "app"
       ["my_app" "metric"] "metric"    "my_app"
       :app/my-metric      "my_metric" "app"
       :my-app/metric      "metric"    "my_app"))
