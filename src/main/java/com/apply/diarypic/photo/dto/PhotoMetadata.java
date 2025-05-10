package com.apply.diarypic.photo.dto;

public class PhotoMetadata {
    private Location location;
    private String shootingDateTime;

    // Location 객체 내부 클래스 또는 별도 클래스
    public static class Location {
        private double latitude;
        private double longitude;

        // getters, setters
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
    }

    // getters, setters
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public String getShootingDateTime() { return shootingDateTime; }
    public void setShootingDateTime(String shootingDateTime) { this.shootingDateTime = shootingDateTime; }
}
