(ns bench
  (:require [clojure.test.check.generators :as gen]
            [iapetos.core :as prometheus]
            [iapetos.registry :as r]
            [iapetos.registry.collectors :as c]
            [iapetos.test.generators :as g]
            [jmh.core :as jmh])
  (:import [iapetos.registry IapetosRegistry]
           [one.profiler AsyncProfiler]))

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

(def metric-label
  (gen/fmap
   #(str "Label_" %)
   (gen/not-empty gen/string-alphanumeric)))

(defn labels
  [labels-count]
  (gen/generate
   (gen/map metric-label
            (gen/return "label-value")
            {:num-elements labels-count})))

(defn label-names
  [labels]
  (keys labels))

;; JMH fns

(defn collectors
  [^IapetosRegistry registry]
  (.-collectors registry))

(defn register-collectors
  ([metrics]
   (register-collectors metrics []))
  ([metrics label-names]
   (reduce (fn [reg metric]
             (r/register reg metric (prometheus/counter metric {:labels label-names})))
           (r/create)
           metrics)))

(defn lookup
  [collectors metric]
  (c/lookup collectors metric {}))

(defn by
  [collectors metric labels]
  (c/by collectors metric labels {}))

(def bench-env
  {:benchmarks [#_#_{:name :registry-lookup
                 :fn   `lookup
                 :args [:state/collectors :state/metric]}

                {:name :dirty-registry-lookup
                 :fn   `lookup
                 :args [:state/dirty-collectors :state/dirty-metric]}

                {:name :registry-by
                 :fn   `by
                 :args [:state/labeled-collectors :state/metric :state/labels]}]

   :states {:dirty-metrics    {:fn `dirty-metrics :args [:param/metric-count]}
            :dirty-metric     {:fn `rand-nth :args [:state/dirty-metrics]}
            :dirty-registry   {:fn `register-collectors :args [:state/dirty-metrics]}
            :dirty-collectors {:fn `collectors :args [:state/dirty-registry]}

            :labels             {:fn `labels :args [:param/label-count]}
            :label-names        {:fn `label-names :args [:state/labels]}
            :labeled-registry   {:fn `register-collectors :args [:state/metrics :state/label-names]}
            :labeled-collectors {:fn `collectors :args [:state/labeled-registry]}

            :metrics    {:fn `metrics :args [:param/metric-count]}
            :metric     {:fn `rand-nth :args [:state/metrics]}
            :registry   {:fn `register-collectors :args [:state/metrics]}
            :collectors {:fn `collectors :args [:state/registry]}}

   :params {:metric-count 500
            :label-count 10}

   :options {:registry-lookup       {:measurement {:iterations 1000}}
             :dirty-registry-lookup {:measurement {:iterations 1000}}
             :registry-by           {:measurement {:iterations 1000}}
             :jmh/default           {:mode             :average
                                     :output-time-unit :us
                                     :measurement      {:iterations 1000
                                                        :count      1}}}})

(def bench-opts
  {:type   :quick
   :params {:metric-count 500
            :label-count 10}})

(defn profile
  [n concurrency scenario]
  (let [profiler (AsyncProfiler/getInstance)
        labels (labels 10)
        metrics (metrics 10)
        registry (register-collectors metrics (label-names labels))
        collectors (collectors registry)]

    (.execute profiler
              (format "start,jfr,event=cpu,alloc,lock,file=./dev/%s.jfr"
                      scenario))

    (run! deref
          (mapv (fn [_]
                  (future
                    (let [metric (rand-nth metrics)]
                      (dotimes [_ n]
                        (by collectors metric labels)))))
                (range concurrency)))

    (.execute profiler "stop")))

(comment

  ;; $ lein with-profile +dev repl
  ;;

  (require '[jmh.core :as jmh]
           '[bench :as bench]
           '[clojure.pprint :as pprint])

  (->> (jmh/run bench/bench-env bench/bench-opts)
       (map #(select-keys % [:fn :mode :name :score]))
       (pprint/pprint))

  (bench/profile 1000000 4 "collector-labeling")

  )
