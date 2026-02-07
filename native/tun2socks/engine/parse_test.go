package engine

import (
	"net/netip"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseMulticastGroups(t *testing.T) {
	tests := []struct {
		name      string
		input     []string
		expected  []netip.Addr
		expectErr bool
	}{
		{
			name:     "Empty input",
			input:    []string{},
			expected: []netip.Addr{},
		},
		{
			name:     "Nil input",
			input:    nil,
			expected: []netip.Addr{},
		},
		{
			name:     "Valid IPv4 multicast",
			input:    []string{"224.0.0.1"},
			expected: []netip.Addr{netip.MustParseAddr("224.0.0.1")},
		},
		{
			name:     "Valid IPv6 multicast",
			input:    []string{"ff02::1"},
			expected: []netip.Addr{netip.MustParseAddr("ff02::1")},
		},
		{
			name:     "Mixed IPv4/IPv6 multicast",
			input:    []string{"224.0.0.1", "ff02::1"},
			expected: []netip.Addr{netip.MustParseAddr("224.0.0.1"), netip.MustParseAddr("ff02::1")},
		},
		{
			name:     "Input with spaces and empty string",
			input:    []string{" 224.0.0.1 ", ""},
			expected: []netip.Addr{netip.MustParseAddr("224.0.0.1")},
		},
		{
			name:      "Invalid IP format",
			input:     []string{"256.0.0.1"},
			expectErr: true,
		},
		{
			name:      "Unicast IP (Not Multicast)",
			input:     []string{"192.168.1.1"},
			expectErr: true,
		},
		{
			name:      "Mixed valid and invalid",
			input:     []string{"224.0.0.1", "192.168.1.1"},
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			groups, err := parseMulticastGroups(tt.input)
			if tt.expectErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, groups)
			}
		})
	}
}
