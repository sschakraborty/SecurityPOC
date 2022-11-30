const express = require('express')
const { fork } = require('child_process')
const _ = require('lodash')

const app = express()
const port = 10200

const primaryObject = {}

app.use(express.json())

app.get('/', (_, response) => {
    response.json(primaryObject)
})

// https://security.snyk.io/vuln/SNYK-JS-LODASH-608086
app.post('/', (request, response) => {
    Object.keys(request.body).map(key => {
        _.set(primaryObject, key, request.body[key])
    })
    response.status(200).end()
})

app.get('/time', (_, response) => {
    let forked = fork('time.js')
    forked.on('message', function(data) {
        response.end(data)
    })
})

app.listen(port, () => {
  console.log(`Demo app listening on port ${port}`)
})
