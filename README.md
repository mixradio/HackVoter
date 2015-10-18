# HackVoter

HackVoter is a simple responsive web app to facilitate voting at hackathons written in Clojure.
It stores data about hacks and votes in `DynamoDB` and has a few configurable elements to allow customisation.

The main parts of the app:

1) the main page that lists the hacks and allows voting within the configured budget

<a href="https://github.com/mixradio/HackVoter/blob/master/screenshots/uservote.png?raw=true"><img src="https://github.com/mixradio/HackVoter/blob/master/screenshots/uservote.png?raw=true" alt="Main page" style="height:75px;"/></a>

2) creation / editing of hack details

<a href="https://github.com/mixradio/HackVoter/blob/master/screenshots/edit.png?raw=true"><img src="https://github.com/mixradio/HackVoter/blob/master/screenshots/edit.png?raw=true" alt="Edit page" style="height:75px;"/></a>

3) admin functions to preview the votes coming in, allow editing and deletion and setting of the `voting stage`.

<a href="https://github.com/mixradio/HackVoter/blob/master/screenshots/adminview.png?raw=true"><img src="https://github.com/mixradio/HackVoter/blob/master/screenshots/adminview.png?raw=true" alt="Admin page" style="height:75px;"/></a>

The voting stage is one of the following:

   * `submission` - allow hacks to be registered before the demos and voting starts.
   * `votingallowed` - typically when the demos are underway and voting is allowed. Last minute submissions and changes are still allowed.
   * `completed` - no more submissions or votes - the winner is announced!

## Anatomy of a Hack

A hack consists of a `title`, `description`, `creator` and an image - nice and simple.
Behind the scenes, a hack has a `publicid` (used for the voting side of the app) and an `editorid` (a private id that only the creator and admins have access to that allows editing and deletion).
[FilePicker](https://www.filepicker.com/) is used to handle image upload and storage - you'll need to sign up for a free account to get a key to enable this.

## "Security"

This isn't designed to be a bullet-proof web app, but there is validation around voting to stop smart-ass devs trying to cheat ;)

## Configuration

As part of the project properties, the following items can also be configured:

### Storage-related items:
   * `hacks-table` - the name of DynamoDB table name to use to store hack details. The app will automatically create the table as needed.
   * `readalloc-hack` / `writealloc-hack` - read and write capacity to use for the hack table.
   * `hack-votes-table` - the name of DynamoDB table name to use to store votes - again automatically created when needed.
   * `readalloc-vote` / `writealloc-vote` - read and write capacity to use for the votes table.

### Voting-related items:

Voting is based on each person being allocated a certain number of votes to "spend". A limit can optionally be placed on the number votes that can be used on each hack to ensure people don't just vote for one.

   * `currency` - the display name for the voting currency - for example, "you have  5 hack pounds available".
   * `allocation` - the total number of votes each person has to start with.
   * `max-spend` -  the maximum number of votes each person can "spend" on a hack. Set this to the same as `allocation` to remove this limit.

### Admin-related items:

   * `voting-stage` - as described above, one of `submission`, `votingallowed`, `completed`.
   * `admin-key` - a simple approach to locking down admin access - rather than username / password, this key is used to restrict access.
   * `filepicker-key` - your [FilePicker](https://www.filepicker.com/) account key.