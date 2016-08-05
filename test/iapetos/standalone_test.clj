(ns iapetos.standalone-test
  (:require [clojure.test :refer :all]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [iapetos.standalone :as standalone])
  (:import [java.net HttpURLConnection URL]))

(def ^:private port 65432)
(def ^:private path "/prometheus-metrics")
(def ^:private metrics-host (str "http://localhost:" port))
(def ^:private metrics-url (str metrics-host path))

(defn- fetch
  [request-method url]
  (let [connection (.openConnection (URL. url))]
    (try
      (.setRequestMethod connection (.toUpperCase (name request-method)))
      (let [status (.getResponseCode connection)
            body (if (= status 200)
                   (.getInputStream connection)
                   (.getErrorStream connection))]
        [status (slurp body)])
      (finally
        (.disconnect connection)))))

(deftest t-standalone-server
  (let [registry (-> (prometheus/collector-registry)
                     (prometheus/register
                       (prometheus/counter :app/runs-total))
                     (prometheus/inc :app/runs-total))]
    (with-open [_ (standalone/metrics-server
                    registry
                    {:port 65432
                     :path "/prometheus-metrics"})]
      (is (= [200 (export/text-format registry)] (fetch :get metrics-url)))
      (is (= [405 "Method not allowed: POST" (fetch :post metrics-url)]))
      (is (= [404 (str "Not found: " path "/_info")]
             (fetch :get (str metrics-url "/_info"))))
      (is (= [404 "Not found: /"] (fetch :get metrics-host))))))
