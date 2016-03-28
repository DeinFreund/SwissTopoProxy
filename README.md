# SwissTopoProxy
This tool allows you to use the online map from map.geo.admin.ch in (mobile) map viewers by converting it to google map-like tiles(Mercator). The java application creates a fake http server that serves jpeg images at different zoom, x and y values.

The bash script fetches tiles from their online server for local caching.
