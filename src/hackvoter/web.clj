(ns hackvoter.web
  (:require [compojure
             [core :refer [defroutes GET POST PUT DELETE]]
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
            [hackvoter.data :as data]
            [hackvoter.html :as html]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.format :refer [formatters unparse]]
            [ring.middleware
             [format-params :refer [wrap-json-kw-params]]
             [json :refer [wrap-json-response]]
             [params :refer [wrap-params]]]
            [ring.util.response :refer [resource-response]]))

(def version
  (setup/version "hackvoter"))

(defn- healthcheck
  []
  (let [body {:name "hackvoter"
              :version version
              :success true
              :dependencies []}]
    {:headers {"content-type" "application/json"}
     :status (if (:success body) 200 500)
     :body body}))

(defn- new-uuid[]
  (str (java.util.UUID/randomUUID)))

(defn- wants-json [headers]
  (> (.indexOf (get headers "accept") "application/json") -1))

(defn get-userid [cookie]
  (let [crumbs (when-not (nil? cookie) (str/split cookie #"userid="))
        userid (when (> (count crumbs) 1) (last crumbs))]
    (prn (str "get-userid " cookie " -> " userid))
    (if-not (nil? userid) userid (new-uuid))))

(defn- get-userid-from-headers [headers]
  (get-userid (get headers "cookie")))

(defn- check-admin-auth [adminkey]
  (zero? (compare adminkey (env :admin-key))))

(defn- get-hack-list [headers params adminview]
    (let [isjson (wants-json headers)
          config (data/get-config-items)
          userid (get-userid-from-headers headers)
          hacks (data/list-hacks adminview)
          content-type (if isjson "application/json" "text/html")
          body (if isjson {:config config :items hacks} (html/format-hacks hacks config adminview))]
          (prn (str "get-hack-list adminview=" adminview " userid=" userid))
      {:headers (if adminview
                  {"content-type" content-type}
                  {"content-type" content-type, "set-cookie" (str "userid=" userid ";expires=" (unparse (formatters :rfc822) (time/plus (time/now) (time/years 5))))})
       :status 200
       :body body}))

(defroutes routes
  (GET "/healthcheck"
       [] (healthcheck))

  (GET "/ping" [] "pong")

  (GET "/"
    {:keys [headers params] :as request}
    (get-hack-list headers params false))

  (GET "/admin" [] (html/get-not-authorised))

  (GET "/admin/:adminkey"
    {:keys [headers params] :as request}
    (if (check-admin-auth (:adminkey params))
      (get-hack-list headers params true)
      (html/get-not-authorised)))

  (PUT "/admin/:adminkey/stage/:stage" [adminkey stage]
    (if (check-admin-auth adminkey)
      (if (str/blank? stage)
        {:status 400}
        (do (data/update-voting-stage stage)
          {:status 204}))
      (html/get-not-authorised)))

  (GET "/admin/:adminkey/delete/:editorid" ; yep, it's not RESTful, but it's simple and let's us redirect through the browser
    [adminkey editorid]
    (when (check-admin-auth adminkey) (data/delete-hack-and-votes editorid))
    (if (check-admin-auth adminkey)
      {:status 302 :headers {"location" (str "/admin/" adminkey)}}
      (html/get-not-authorised)))

  (GET "/hacks/new" []
    {:status 302 :headers {"location" (str "/hacks/" (new-uuid))}})

  (GET "/hacks/:editorid"
      {:keys [headers params] :as request}
      (let [editorid (get params :editorid)
            hack (data/get-hack-by-editorid editorid)
            editablehack (if (nil? (:publicid hack)) {:editorid editorid} hack)]
        (html/get-edit-page editablehack nil)))

  (POST "/hacks/:editorid"
      {:keys [headers params] :as request}
      (let [editorid (get params :editorid)
            hack (data/get-hack-by-editorid editorid)
            isnew (nil? (:publicid hack))
            newtitle (get params "title")
            newdesc (get params "desc")
            newcreator (get params "creator")
            newimgurl (get params "imgurl")
            valid (and (and (not (str/blank? newtitle)) (not (str/blank? newdesc))) (not (str/blank? newcreator)) (not (str/blank? newimgurl)))
            mergedhack (assoc (assoc (assoc (assoc hack :title newtitle) :description newdesc) :creator newcreator) :imgurl newimgurl)
            editorhack (assoc mergedhack :editorid editorid)
            finalhack (if (and valid isnew) (assoc editorhack :publicid (new-uuid)) editorhack)]
        (prn params)
        (if valid
          (do (data/store-hack finalhack)
              (html/get-edit-page finalhack "Changes saved."))
          (html/get-edit-page finalhack "Changed not saved due to missing items - please enter all values including an image."))))

  (POST "/hacks/:publicid/votes"
    {:keys [headers params] :as request}
    (let [userid (get-userid-from-headers headers)
          id (get params :publicid)
          strvotes (get params "votes")
          votes (Integer. (re-find  #"\d+" strvotes))
          hackexists (not (nil? (data/get-hack-by-publicid id)))
          valid (and (number? votes) hackexists)]
      (when valid (data/store-vote userid id votes))
        (if valid
         {:headers {"content-type" "application/json"}
          :status 200
          :body (data/get-user-votes userid)}
         {:headers {"content-type" "application/json"}
          :status 400})))

  (GET "/votes"
    {:keys [headers params] :as request}
    (let [votes (data/get-user-votes (get-userid-from-headers headers))]
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
