public class PerformanceMetrics {
    private long totalLatency;
    private int totalQueries;
    private int successfulQueries;
    private long totalStabilizationTime;
    private int stabilizationEvents;

    public PerformanceMetrics() {
        this.totalLatency = 0;
        this.totalQueries = 0;
        this.successfulQueries = 0;
        this.totalStabilizationTime = 0;
        this.stabilizationEvents = 0;
    }

    public void updateMetrics(long latency, boolean isSuccess, long stabilizationTime) {
        this.totalLatency += latency;
        this.totalQueries++;
        if (isSuccess) {
            this.successfulQueries++;
        }
        if (stabilizationTime > 0) {
            this.totalStabilizationTime += stabilizationTime;
            this.stabilizationEvents++;
        }
    }

    public double getAverageLatency() {
        return totalQueries > 0 ? (double) totalLatency / totalQueries : 0;
    }

    public double getSuccessRate() {
        return totalQueries > 0 ? (double) successfulQueries / totalQueries * 100 : 0;
    }

    public double getAverageStabilizationTime() {
        return stabilizationEvents > 0 ? (double) totalStabilizationTime / stabilizationEvents : 0;
    }

    @Override
    public String toString() {
        return "Performance Metrics:\n" +
               "Average Latency = " + getAverageLatency() + " ns\n" +
               "Success Rate = " + getSuccessRate() + "%\n" +
               "Average Stabilization Time = " + getAverageStabilizationTime() + " ms\n";
    }
}
