# Almanac

A caching service for FullContact person API

## Prerequisites

- API key received on FullContact
- Up and running database (Amazon RDS prefferable)
- lein-environ plugin (optional) `[lein-environ "0.4.0"]` in lein plugins section
- lein-lobos plugin (optional)  `[lein-lobos "1.0.0-beta1"]` in lein plugins section

### RDS Setup

Note, that it requires manual RDS instance configuration:

- creating instance
- creating databases (if there are different for production/development/testing)
- adding server IP to the authorized in Security options

## Configuration

All configuration is read through environtment variables.

So there are 2 ways to specify them: either plain environment variables or using

#### Environment variables

`FULLCONTACT_APIKEY` - API key for FullContact
`RDS_HOST` - SQL server host name
`RDS_DB`- SQL server db name
`RDS_USER` - SQL user
`RDS_PASS` SQL password

#### Leiningen profiles

As `leiningen` allows to specify profiles in `~/.lein/profiles.clj` it is the simpliest way to setup it, for example like this:

    {:dev {:env {:fullcontact-apikey "3f68888499999752f"
                 :rds-host "somewhere0b.us-west-2.rds.amazonaws.com"
                 :rds-user "user"
                 :rds-pass "password"
                 :rds-db   "almanac"
                 :cache-storage "rds"}}
     :test {:env  {:rds-db "almanac_test"}}}

It also allows to run tests on a separate (even local) database.

## Running

Before running the server, all schema  migrations should be performed.
There are 2 options

### From REPL

    (require '[lobos.core :refer [migrate]])
    (migrate)

### Using lein-lobos plugin

    Add `[lein-lobos "1.0.0-beta1"]` to your lein plugins and perform:

    `lein lobos migrate`

Once all migrations are complete the server can be started as usually like `lein ring server` or `lein with-profile ... ring server`

## License

Copyright © 2013 ZoloLabs
