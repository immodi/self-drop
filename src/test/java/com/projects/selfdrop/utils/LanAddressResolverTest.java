package com.projects.selfdrop.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanAddressResolverTest {

    @Test
    void recognisesPrivateIpv4Ranges() {
        assertThat(LanAddressResolver.isPrivateIpv4("192.168.1.104")).isTrue();
        assertThat(LanAddressResolver.isPrivateIpv4("10.0.0.5")).isTrue();
        assertThat(LanAddressResolver.isPrivateIpv4("172.16.0.1")).isTrue();
        assertThat(LanAddressResolver.isPrivateIpv4("172.31.255.254")).isTrue();

        assertThat(LanAddressResolver.isPrivateIpv4("172.15.0.1")).isFalse();
        assertThat(LanAddressResolver.isPrivateIpv4("172.32.0.1")).isFalse();
        assertThat(LanAddressResolver.isPrivateIpv4("192.169.1.1")).isFalse();
        assertThat(LanAddressResolver.isPrivateIpv4("8.8.8.8")).isFalse();
        assertThat(LanAddressResolver.isPrivateIpv4(null)).isFalse();
    }

    @Test
    void rejectsUnusableIpv4Addresses() {
        assertThat(LanAddressResolver.isUsableIpv4("192.168.1.104")).isTrue();
        assertThat(LanAddressResolver.isUsableIpv4("10.0.0.5")).isTrue();

        assertThat(LanAddressResolver.isUsableIpv4("0.0.0.0")).isFalse();
        assertThat(LanAddressResolver.isUsableIpv4("127.0.0.1")).isFalse();
        assertThat(LanAddressResolver.isUsableIpv4("169.254.10.20")).isFalse();
        assertThat(LanAddressResolver.isUsableIpv4("255.255.255.255")).isFalse();
        assertThat(LanAddressResolver.isUsableIpv4(" ")).isFalse();
        assertThat(LanAddressResolver.isUsableIpv4(null)).isFalse();
    }

    @Test
    void skipsVirtualInterfaces() {
        assertThat(LanAddressResolver.isVirtualInterface("docker0")).isTrue();
        assertThat(LanAddressResolver.isVirtualInterface("br-1a2b3c")).isTrue();
        assertThat(LanAddressResolver.isVirtualInterface("veth1234")).isTrue();
        assertThat(LanAddressResolver.isVirtualInterface("tun0")).isTrue();
        assertThat(LanAddressResolver.isVirtualInterface("wg0")).isTrue();
        assertThat(LanAddressResolver.isVirtualInterface("tailscale0")).isTrue();

        assertThat(LanAddressResolver.isVirtualInterface("wlan0")).isFalse();
        assertThat(LanAddressResolver.isVirtualInterface("eth0")).isFalse();
        assertThat(LanAddressResolver.isVirtualInterface("enp3s0")).isFalse();
    }

    @Test
    void prefersTheWifiLanAddressOverOtherPrivateAddresses() {
        int wifi = LanAddressResolver.score("wlan0", "192.168.1.104");
        int ethernet = LanAddressResolver.score("eth0", "10.0.0.5");
        int publicAddress = LanAddressResolver.score("eth0", "203.0.113.10");

        assertThat(wifi).isGreaterThan(ethernet);
        assertThat(ethernet).isGreaterThan(publicAddress);
    }
}
