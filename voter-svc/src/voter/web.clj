(ns voter.web
  (:require [compojure
             [core :refer [defroutes GET POST DELETE]]
             [route :as route]]
            [environ.core :refer [env]]
            [metrics.ring
             [expose :refer [expose-metrics-as-json]]
             [instrument :refer [instrument]]]
            [radix
             [error :refer [error-response wrap-error-handling]]
             [ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
             [reload :refer [wrap-reload]]
             [setup :as setup]]
            [voter.data :as data]
            [voter.html :as html]
            [ring.middleware
             [format-params :refer [wrap-json-kw-params]]
             [json :refer [wrap-json-response]]
             [params :refer [wrap-params]]]))

(def version
  (setup/version "voter"))


(defn- healthcheck
  []
  (let [body {:name "voter"
              :version version
              :success true
              :dependencies []}]
    {:headers {"content-type" "application/json"}
     :status (if (:success body) 200 500)
     :body body}))

(defn- wants-json [headers]
  (> (.indexOf (get headers "accept") "application/json") -1))

(defroutes routes
  (GET "/healthcheck"
       [] (healthcheck))

  (GET "/"
        [] "gets a list of hacks - either json or HTML representation")

  (GET "/hacks/new"
        [] (str "creates a new hack to edit - creates the editor ID and redirects to /hacks/:editorid"))

  (GET "/hacks/:editorid"
        [editorid] (str "shows a hack to edit - HTML representation " editorid))

  (POST "/hacks/:publicid/votes"
        [publicid] (str "adds votes for a hack " publicid))

  (GET "/admin/hacks"
    {:keys [headers params] :as request}
    (let [isjson (wants-json headers)
          data {:config (data/get-config-items) :items (data/list-hacks)}
          content-type (if isjson "application/json" "text/html")
          body (if isjson data (html/format-hacks data true))]
      {:headers {"content-type" content-type}
       :status 200
       :body body}))

  (DELETE "/admin/hacks/:editorid"
        [editorid] (str "delete hack " editorid))
  
  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (wrap-reload)
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-response)
      (wrap-json-kw-params)
      (wrap-params)
      (expose-metrics-as-json)))
