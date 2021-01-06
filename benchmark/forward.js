import http from 'k6/http';
import {check} from "k6";
import {Rate} from "k6/metrics";

const url = `${__ENV.BASE_URL || 'http://localhost:10101'}/api/length/2000/delay/1000`
const statusFailure = new Rate("status_failure_rate");

export default function () {
  const response = http.get(url);

  statusFailure.add(!check(response, {
    "status is 200": (r) => r.status === 200,
  }))
}

export const options = {
  vus: 600,
  stages: [
    {duration: '1m', target: 600},
    {duration: '1m', target: 900},
    {duration: '1m', target: 1200},
    {duration: '1m', target: 1500},
    {duration: '1m', target: 1800},
    {duration: '1m', target: 2100},
    {duration: '1m', target: 1200},
    {duration: '1m', target: 0},
  ],
};

