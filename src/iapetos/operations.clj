(ns iapetos.operations
  (:import [io.prometheus.client
            Counter$Child
            Histogram$Child
            Histogram$Timer
            Gauge$Child
            Gauge$Timer
            Summary$Child
            Summary$Timer]))

;; ## Operation Protocols

(defprotocol ReadableCollector
  (read-value [this]))

(defprotocol IncrementableCollector
  (increment* [this amount]))

(defprotocol DecrementableCollector
  (decrement* [this amount]))

(defprotocol SettableCollector
  (set-value [this value])
  (set-value-to-current-time [this]))

(defprotocol ObservableCollector
  (observe [this amount]))

(defprotocol TimeableCollector
  (start-timer [this]))

;; ## Derived Functions

(defn increment
  ([this] (increment this 1.0))
  ([this amount] (increment* this amount)))

(defn decrement
  ([this] (decrement this 1.0))
  ([this amount] (decrement* this amount)))

;; ## Counter

(extend-type Counter$Child
  ReadableCollector
  (read-value [this]
    (.get ^Counter$Child this))
  IncrementableCollector
  (increment* [this amount]
    (.inc ^Counter$Child this (double amount))))

;; ## Gauge

(extend-type Gauge$Child
  ReadableCollector
  (read-value [this]
    (.get ^Gauge$Child this))

  IncrementableCollector
  (increment* [this amount]
    (.inc ^Gauge$Child this (double amount)))

  DecrementableCollector
  (decrement* [this amount]
    (.dec ^Gauge$Child this (double amount)))

  ObservableCollector
  (observe [this amount]
    (.set ^Gauge$Child this (double amount)))

  SettableCollector
  (set-value [this value]
    (.set ^Gauge$Child this (double value)))
  (set-value-to-current-time [this]
    (.setToCurrentTime ^Gauge$Child this))

  TimeableCollector
  (start-timer [this]
    (let [^Gauge$Timer t (.startTimer ^Gauge$Child this)]
      #(.setDuration t))))

;; ## Histogram

(extend-type Histogram$Child
  ReadableCollector
  (read-value [this]
    (let [^io.prometheus.client.Histogram$Child$Value value
          (.get ^Histogram$Child this)]
      {:sum     (.-sum value)
       :buckets (vec (.-buckets value))}))

  ObservableCollector
  (observe [this amount]
    (.observe ^Histogram$Child this (double amount)))

  TimeableCollector
  (start-timer [this]
    (let [^Histogram$Timer t (.startTimer ^Histogram$Child this)]
      #(.observeDuration t))))

;; ## Summary

(extend-type Summary$Child
  ReadableCollector
  (read-value [this]
    (let [^io.prometheus.client.Summary$Child$Value value
          (.get ^Summary$Child this)]
      {:sum   (.-sum value)
       :count (.-count value)}))

  ObservableCollector
  (observe [this amount]
    (.observe ^Summary$Child this (double amount)))

  TimeableCollector
  (start-timer [this]
    (let [^Summary$Timer t (.startTimer ^Summary$Child this)]
      #(.observeDuration t))))
