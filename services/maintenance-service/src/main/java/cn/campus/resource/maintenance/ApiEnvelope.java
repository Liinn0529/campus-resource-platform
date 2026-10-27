package cn.campus.resource.maintenance;

public record ApiEnvelope<T>(T data, String requestId) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(data, RequestIds.current());
    }
}
