package cn.campus.resource.platform;

record UserAccount(long id, String username, String displayName, String passwordHash,
        boolean enabled, long authVersion) {
    @Override
    public String toString() { return "UserAccount[id=" + id + "]"; }
}
