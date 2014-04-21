%define name [[name]]
%define version [[version]]
%define jarsdir [[dir.system.jar]]
%define docsdir [[dir.system.doc]]
%define manual [[manual]]
%define doc [[doc]]

Summary: An extension of Java RMI with Distributed Transactions
Name: %{name}
Version: %{version}
Release: 1
License: GPL
Group: Development/Java
Source: http://www.cs.put.poznan.pl/pawelw/atomicrmi/atomic-rmi-2.0.tar.gz
URL: https://www.soa.edu.pl/
Vendor: Poznań University of Technology, Institute of Computing Science
Packager: Konrad Siek <konrad.siek@cs.put.poznan.pl>
Requires: java, cglib >= [[cglib.version]]
Provides: %{name}
BuildArch: noarch

%description
Provides constructs allowing the programmer to declare a series of method calls
on remote objects as a distributed transaction, executed in isolation with 
respect to other transactions.

#%build

%install
mkdir -p %{buildroot}%{jarsdir}/
mkdir -p %{buildroot}%{docsdir}/%{name}/
cp %{name}-%{version}.jar %{buildroot}%{jarsdir}/
#cp -r %{manual} %{buildroot}%{docsdir}/%{name}/
cp -r %{doc} %{buildroot}%{docsdir}/%{name}/

%clean
rm -rf %{buildroot}

%files
%{jarsdir}/%{name}-%{version}.jar
#%{docsdir}/%{name}/%{manual}
%{docsdir}/%{name}/%{doc}
