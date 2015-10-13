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
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.format :refer [formatters unparse]]
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

(defn- new-uuid[]
  (str (java.util.UUID/randomUUID)))

(defn- wants-json [headers]
  (> (.indexOf (get headers "accept") "application/json") -1))

(defn- get-userid [headers]
  (let [cookie (get headers "cookie")
        crumbs (when (not (nil? cookie)) (str/split cookie #"userid="))
        userid (when (> (count crumbs) 0) (last crumbs))]
    (prn (str "get-userid " cookie " -> " userid))
    (if (not (nil? userid)) userid (new-uuid))))

(defn- check-admin-auth [adminkey]
  (== 0 (compare adminkey (env :admin-key))))

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
                  {"content-type" content-type, "set-cookie" (str "userid=" userid ";expires=" (unparse (formatters :rfc822) (time/plus (time/now) (time/years 5))))})
       :status 200
       :body body}))

(defroutes routes
  (GET "/healthcheck"
       [] (healthcheck))

  (GET "/"
    {:keys [headers params] :as request}
    (get-hack-list headers params false))

  (GET "/admin" [] (html/get-not-authorised))

  (GET "/admin/:adminkey"
    {:keys [headers params] :as request}
    (if (check-admin-auth (:adminkey params))
      (get-hack-list headers params true)
      (html/get-not-authorised)))
  
  (GET "/admin/:adminkey/delete/:editorid" ; yep, it's not RESTful, but it's simple!
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
            valid (and (and (not (str/blank? newtitle)) (not (str/blank? newdesc))) (not (str/blank? newcreator)))
            mergedhack (assoc (assoc (assoc hack :title newtitle) :description newdesc) :creator newcreator)
            editorhack (assoc mergedhack :editorid editorid)
            finalhack (if (and valid isnew) (assoc editorhack :publicid (new-uuid)) editorhack)]
        (if valid
          (do (data/store-hack finalhack)
              (html/get-edit-page finalhack "Changes saved."))
          (html/get-edit-page finalhack "Changed not saved due to missing fields."))))

  (POST "/hacks/:publicid/votes"
    {:keys [headers params] :as request}
    (let [userid (get-userid headers)
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
    (let [votes (data/get-user-votes (get-userid headers))]
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
