(ns iapetos.standalone
  (:require [iapetos.collector.ring :as ring]
            [clojure.java.io :as io])
  (:import [com.sun.net.httpserver
            HttpHandler
            HttpServer
            HttpExchange]
           [java.net InetSocketAddress]))

;; ## Handler

(defn- write-headers!
  [^HttpExchange e {:keys [status headers ^bytes body]}]
  (let [h (.getResponseHeaders e)
        content-length (alength body)]
    (doseq [[header value] headers]
      (.set h (str header) (str value)))
    (.set h "Content-Length" (str content-length))
    (.sendResponseHeaders e (int status) content-length)))

(defn- write-body!
  [^HttpExchange e {:keys [^bytes body]}]
  (with-open [out (.getResponseBody e)]
    (.write out body)
    (.flush out)))

(defn- plain-response
  [status & text]
  {:status  status
   :headers {"Content-Type" "text/plain; charset=UTF-8"}
   :body     (apply str text)})

(defn- write-response!
  [^HttpExchange e registry path]
  (with-open [_ e]
    (let [request-method (.getRequestMethod e)
          request-path  (.getPath (.getRequestURI e))
          response
          (-> (try
                (cond (not= request-path path)
                      (plain-response 404 "Not found: " request-path)

                      (not= request-method "GET")
                      (plain-response 405 "Method not allowed: " request-method)

                      :else
                      (ring/metrics-response registry))
                (catch Throwable t
                  (plain-response 500 (pr-str t))))
              (update :body #(.getBytes ^String % "UTF-8")))]
      (doto e
        (write-headers! response)
        (write-body! response)))))

(defn- metrics-handler
  [registry path]
  (reify HttpHandler
    (handle [_ e]
      (Thread/sleep 100)
      (write-response! e registry path))))

;; ## Server

(defn metrics-server
  "Expose the metrics contained within the given collector registry using
   the given port and path.

   Returns a handle on the standalone server, implementing `java.io.Closeable`."
  [registry & [{:keys [^long port
                       ^String path
                       ^long queue-size
                       ^java.util.concurrent.ExecutorService executor]
                :or {port       8080,
                     path       "/metrics"
                     queue-size 5}}]]
  (let [handler (metrics-handler registry path)
        server (doto (HttpServer/create)
                 (.bind (InetSocketAddress. port) queue-size)
                 (.createContext "/" handler)
                 (.setExecutor executor)
                 (.start))]
    (reify java.io.Closeable
      (close [_]
        (.stop server 0)))))
