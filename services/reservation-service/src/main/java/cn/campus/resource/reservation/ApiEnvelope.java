package cn.campus.resource.reservation;

public record ApiEnvelope<T>(T data, String requestId) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(data, RequestIds.current());
    }
}
