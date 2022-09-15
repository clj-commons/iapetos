(ns bench
  (:require [clojure.test.check.generators :as gen]
            [iapetos.core :as prometheus]
            [iapetos.registry :as r]
            [iapetos.registry.collectors :as c]
            [iapetos.test.generators :as g]
            [jmh.core :as jmh])
  (:import [iapetos.registry IapetosRegistry]))

(defn metrics
  [metric-count]
  (gen/sample g/metric metric-count))

(def dirty-string-metric
  (gen/let [first-char    gen/char-alpha
            invalid-chars (gen/return (apply str (map char (range 33 45))))
            last-char     gen/char-alphanumeric
            rest-chars    gen/string-alphanumeric]
    (gen/return
     (str
      (apply str first-char invalid-chars rest-chars)
      last-char))))

(defn dirty-metrics
  [metric-count]
  (gen/sample dirty-string-metric metric-count))

;; JMH fns

(defn collectors
  [^IapetosRegistry registry]
  (.-collectors registry))

(defn register-collectors
  [metrics]
  (reduce (fn [reg metric]
            (r/register reg metric (prometheus/counter metric)))
          (r/create)
          metrics))

(defn lookup
  [collectors metric]
  (c/lookup collectors metric {}))

(def bench-env
  {:benchmarks [{:name :registry-lookup
                 :fn   `lookup
                 :args [:state/collectors :state/metric]}

                {:name :dirty-registry-lookup
                 :fn   `lookup
                 :args [:state/dirty-collectors :state/dirty-metric]}]

   :states {:dirty-metrics    {:fn `dirty-metrics :args [:param/metric-count]}
            :dirty-metric     {:fn `rand-nth :args [:state/dirty-metrics]}
            :dirty-registry   {:fn `register-collectors :args [:state/dirty-metrics]}
            :dirty-collectors {:fn `collectors :args [:state/dirty-registry]}

            :metrics    {:fn `metrics :args [:param/metric-count]}
            :metric     {:fn `rand-nth :args [:state/metrics]}
            :registry   {:fn `register-collectors :args [:state/metrics]}
            :collectors {:fn `collectors :args [:state/registry]}}

   :params {:metric-count 500}

   :options {:registry-lookup       {:measurement {:iterations 1000}}
             :dirty-registry-lookup {:measurement {:iterations 1000}}
             :jmh/default           {:mode             :average
                                     :output-time-unit :us
                                     :measurement      {:iterations 1000
                                                        :count      1}}}})

(def bench-opts
  {:type   :quick
   :params {:metric-count 500}})

(comment

  (map #(select-keys % [:fn :mode :name :score]) (jmh/run bench-env bench-opts))

  )
