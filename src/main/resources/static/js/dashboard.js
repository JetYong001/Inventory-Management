const chartColours = {
  sales: '#1466d8',
  purchases: '#8352e8',
  grid: '#dfe6ef',
  tick: '#62758a',
  inStock: '#169b7c',
  lowStock: '#f5a524',
  outOfStock: '#e65663'
};

const periods = {
  'six-months': {
    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    sales: [14500, 19000, 17000, 22000, 25000, 27500],
    purchases: [12000, 14000, 13000, 17000, 20000, 22000]
  },
  quarter: {
    labels: ['Apr', 'May', 'Jun'],
    sales: [22000, 25000, 27500],
    purchases: [17000, 20000, 22000]
  },
  year: {
    labels: ['Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    sales: [11000, 13000, 14500, 16000, 18500, 21000, 14500, 19000, 17000, 22000, 25000, 27500],
    purchases: [9000, 11000, 10500, 13000, 15000, 18000, 12000, 14000, 13000, 17000, 20000, 22000]
  }
};

const salesCanvas = document.querySelector('#sales-purchases-chart');
const stockCanvas = document.querySelector('#stock-status-chart');
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const salesChart = new Chart(salesCanvas, {
  type: 'line',
  data: {
    labels: periods['six-months'].labels,
    datasets: [
      {
        label: 'Sales',
        data: periods['six-months'].sales,
        borderColor: chartColours.sales,
        backgroundColor: 'rgba(20, 102, 216, 0.08)',
        borderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 5,
        pointBackgroundColor: chartColours.sales,
        fill: true,
        tension: 0.32
      },
      {
        label: 'Purchases',
        data: periods['six-months'].purchases,
        borderColor: chartColours.purchases,
        backgroundColor: 'transparent',
        borderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 5,
        pointBackgroundColor: chartColours.purchases,
        fill: false,
        tension: 0.32
      }
    ]
  },
  options: {
    responsive: true,
    maintainAspectRatio: false,
    animation: reduceMotion ? false : { duration: 450 },
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label(context) {
            return `${context.dataset.label}: RM ${context.parsed.y.toLocaleString('en-MY')}`;
          }
        }
      }
    },
    scales: {
      x: {
        grid: { display: false },
        border: { display: false },
        ticks: {
          color: chartColours.tick,
          font: { size: 10, weight: '600' }
        }
      },
      y: {
        min: 0,
        max: 30000,
        ticks: {
          stepSize: 10000,
          color: chartColours.tick,
          font: { size: 10, weight: '600' },
          callback(value) {
            return value === 0 ? '0' : `${value / 1000}k`;
          }
        },
        grid: { color: chartColours.grid },
        border: { display: false }
      }
    }
  }
});

new Chart(stockCanvas, {
  type: 'doughnut',
  data: {
    labels: ['In stock', 'Low stock', 'Out of stock'],
    datasets: [{
      data: [78, 12, 10],
      backgroundColor: [
        chartColours.inStock,
        chartColours.lowStock,
        chartColours.outOfStock
      ],
      borderWidth: 0,
      hoverOffset: 5
    }]
  },
  options: {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '72%',
    animation: reduceMotion ? false : { duration: 450 },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label(context) {
            return `${context.label}: ${context.parsed} products`;
          }
        }
      }
    }
  }
});

document.querySelector('#range-select').addEventListener('change', (event) => {
  const selected = periods[event.target.value];

  salesChart.data.labels = selected.labels;
  salesChart.data.datasets[0].data = selected.sales;
  salesChart.data.datasets[1].data = selected.purchases;

  salesChart.update();
});