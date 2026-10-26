package cn.campus.resource.classroom;

public record ApiEnvelope<T>(T data, String requestId) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(data, RequestIds.current());
    }
}
