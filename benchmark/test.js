import http from 'k6/http';
import {check, randomSeed} from "k6";

randomSeed(0)

/**
 * @return {string} the host for the test
 */
function getHost() {
  switch (__ENV.TYPE) {
    case 'forward':
      return 'localhost:10101'
    case 'blocking':
      return 'localhost:10102'
    case 'nio':
      return 'localhost:10103'
    case 'reactive':
      return 'localhost:10104'
    default:
      return 'localhost:8080'
  }
}

const prefix = `http://${getHost()}/api/length/2000/delay/2000/distinct`

/**
 * @param distinct amount of url 0-distinct that can be created.
 * @return {string}
 */
function getUrl(distinct) {
  return `${prefix}/${Math.round(Math.random() * distinct)}`
}

export default function () {
  const response = http.get(getUrl(5000));
  check(response, {
    "status is 200": (r) => r.status === 200,
  })
}

export const options = {
  stages: [
    {duration: '1m', target: 300},
    {duration: '1m', target: 600},
    {duration: '1m', target: 900},
    {duration: '1m', target: 1200},
    {duration: '1m', target: 1200},
    {duration: '3m', target: 0},
  ],
};

