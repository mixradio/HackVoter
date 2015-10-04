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
             [params :refer [wrap-params]]]
            [ring.util.response :refer [resource-response]]))

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

(defn- get-userid [headers]
  (let [prefix "userid="
        cookie (get headers "cookie")]
    (if (and (not (nil? cookie)) (.startsWith cookie prefix)) (subs cookie (count prefix)) (str (java.util.UUID/randomUUID)))))

(defn- get-hack-list [headers params adminview]
    (let [isjson (wants-json headers)
          config (data/get-config-items)
          userid (get-userid headers)
          hacks (data/list-hacks adminview)
          content-type (if isjson "application/json" "text/html")
          body (if isjson {:config config :items hacks} (html/format-hacks hacks config adminview))]
          (prn (str "get-hack-list adminview=" adminview " userid=" userid))
      {:headers (if adminview
                  {"content-type" content-type}
                  {"content-type" content-type, "set-cookie" (str "userid=" userid)})
       :status 200
       :body body}))

(defroutes routes
  (GET "/healthcheck"
       [] (healthcheck))

  (GET "/"
    {:keys [headers params] :as request}
    (get-hack-list headers params false))

  (GET "/admin/hacks"
    {:keys [headers params] :as request}
    (get-hack-list headers params true))

  (DELETE "/admin/hacks/:editorid"
        [editorid] (str "delete hack " editorid))

  (GET "/hacks/new"
        [] (str "creates a new hack to edit - creates the editor ID and redirects to /hacks/:editorid"))

  (GET "/hacks/:editorid"
        [editorid] (str "shows a hack to edit - HTML representation " editorid))

  (POST "/hacks/:publicid/votes"
        [publicid] (str "adds votes for a hack " publicid))

  (GET "/votes"
    {:keys [headers params] :as request}
    (let [votes (data/get-user-votes-json (get-userid headers))]
      {:headers {"content-type" "application/json"}
       :status 200
       :body votes}))

  (GET "/error" []
       (html/get-error))

  (GET "/favicon.ico" []
       (resource-response "favicon.ico" ))

  (route/resources "/assets")
  
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
