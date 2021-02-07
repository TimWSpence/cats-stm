# Credit to https://github.com/profunktor/redis4cats/blob/master/shell.nix
let
  jdk11 = self: super: rec {
    jre = pkgs.jdk11;
  };

  nixpkgs = fetchTarball {
    name = "nixpkgs-20-09";
    url = "https://github.com/NixOS/nixpkgs/archive/20.09.tar.gz";
    sha256 = "1wg61h4gndm3vcprdcg7rc4s1v3jkm5xd7lw8r2f67w502y94gcy";
  };
  pkgs = import nixpkgs { overlays = [ jdk11 ]; };
in
pkgs.mkShell {
  buildInputs = with pkgs; [
    sbt
    nodejs-12_x
    yarn
    yarn2nix
  ];
}
