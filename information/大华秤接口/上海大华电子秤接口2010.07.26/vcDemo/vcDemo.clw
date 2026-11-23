; CLW file contains information for the MFC ClassWizard

[General Info]
Version=1
LastClass=CVcDemoDlg
LastTemplate=CDialog
NewFileInclude1=#include "stdafx.h"
NewFileInclude2=#include "vcDemo.h"

ClassCount=4
Class1=CVcDemoApp
Class2=CVcDemoDlg
Class3=CAboutDlg

ResourceCount=3
Resource1=IDD_ABOUTBOX
Resource2=IDR_MAINFRAME
Resource3=IDD_VCDEMO_DIALOG

[CLS:CVcDemoApp]
Type=0
HeaderFile=vcDemo.h
ImplementationFile=vcDemo.cpp
Filter=N

[CLS:CVcDemoDlg]
Type=0
HeaderFile=vcDemoDlg.h
ImplementationFile=vcDemoDlg.cpp
Filter=D
BaseClass=CDialog
VirtualFilter=dWC

[CLS:CAboutDlg]
Type=0
HeaderFile=vcDemoDlg.h
ImplementationFile=vcDemoDlg.cpp
Filter=D

[DLG:IDD_ABOUTBOX]
Type=1
Class=CAboutDlg
ControlCount=4
Control1=IDC_STATIC,static,1342177283
Control2=IDC_STATIC,static,1342308480
Control3=IDC_STATIC,static,1342308352
Control4=IDOK,button,1342373889

[DLG:IDD_VCDEMO_DIALOG]
Type=1
Class=CVcDemoDlg
ControlCount=2
Control1=IDC_BUTTON_DOWN,button,1342242816
Control2=IDC_BUTTON_RECEIVE,button,1342242816

