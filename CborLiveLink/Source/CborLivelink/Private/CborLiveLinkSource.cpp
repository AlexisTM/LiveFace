// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

#include "CborLivelinkSource.h"

#include "ILiveLinkClient.h"
#include "LiveLinkTypes.h"
#include "Roles/LiveLinkAnimationRole.h"
#include "Roles/LiveLinkAnimationTypes.h"

#include "Async/Async.h"
#include "Common/UdpSocketBuilder.h"
#include "HAL/RunnableThread.h"
#include "Sockets.h"
#include "SocketSubsystem.h"
#include "json.hpp"

#define LOCTEXT_NAMESPACE "CborLivelinkSource"

#define RECV_BUFFER_SIZE 1024 * 1024

using nlohmann::json;

FCborLivelinkSource::FCborLivelinkSource(FIPv4Endpoint InEndpoint)
: Socket(nullptr)
, Stopping(false)
, Thread(nullptr)
, WaitTime(FTimespan::FromMilliseconds(100))
{
	// defaults
	DeviceEndpoint = InEndpoint;

	SourceStatus = LOCTEXT("SourceStatus_DeviceNotFound", "Device Not Found");
	SourceType = LOCTEXT("CborLivelinkSourceType", "Cbor LiveLink");
	SourceMachineName = LOCTEXT("CborLivelinkSourceMachineName", "localhost");

	//setup socket
	if (DeviceEndpoint.Address.IsMulticastAddress())
	{
		Socket = FUdpSocketBuilder(TEXT("CBORSOCKET"))
			.AsNonBlocking()
			.AsReusable()
			.BoundToPort(DeviceEndpoint.Port)
			.WithReceiveBufferSize(RECV_BUFFER_SIZE)

			.BoundToAddress(FIPv4Address::Any)
			.JoinedToGroup(DeviceEndpoint.Address)
			.WithMulticastLoopback()
			.WithMulticastTtl(2);

	}
	else
	{
		Socket = FUdpSocketBuilder(TEXT("SOCKET"))
			.AsNonBlocking()
			.AsReusable()
			.BoundToAddress(DeviceEndpoint.Address)
			.BoundToPort(DeviceEndpoint.Port)
			.WithReceiveBufferSize(RECV_BUFFER_SIZE);
	}

	RecvBuffer.SetNumUninitialized(RECV_BUFFER_SIZE);

	if ((Socket != nullptr) && (Socket->GetSocketType() == SOCKTYPE_Datagram))
	{
		SocketSubsystem = ISocketSubsystem::Get(PLATFORM_SOCKETSUBSYSTEM);

		Start();

		SourceStatus = LOCTEXT("SourceStatus_Receiving", "Receiving");
	}
}

FCborLivelinkSource::~FCborLivelinkSource()
{
	Stop();
	if (Thread != nullptr)
	{
		Thread->WaitForCompletion();
		delete Thread;
		Thread = nullptr;
	}
	if (Socket != nullptr)
	{
		Socket->Close();
		ISocketSubsystem::Get(PLATFORM_SOCKETSUBSYSTEM)->DestroySocket(Socket);
	}
}

void FCborLivelinkSource::ReceiveClient(ILiveLinkClient* InClient, FGuid InSourceGuid)
{
	Client = InClient;
	SourceGuid = InSourceGuid;
}


bool FCborLivelinkSource::IsSourceStillValid() const
{
	// Source is valid if we have a valid thread and socket
	bool bIsSourceValid = !Stopping && Thread != nullptr && Socket != nullptr;
	return bIsSourceValid;
}


bool FCborLivelinkSource::RequestSourceShutdown()
{
	Stop();

	return true;
}
// FRunnable interface

void FCborLivelinkSource::Start()
{
	ThreadName = "Cbor UDP Receiver ";
	ThreadName.AppendInt(FAsyncThreadIndex::GetNext());

	Thread = FRunnableThread::Create(this, *ThreadName, 128 * 1024, TPri_AboveNormal, FPlatformAffinity::GetPoolThreadMask());
}

void FCborLivelinkSource::Stop()
{
	Stopping = true;
}

uint32 FCborLivelinkSource::Run()
{
	TSharedRef<FInternetAddr> Sender = SocketSubsystem->CreateInternetAddr();

	while (!Stopping)
	{
		if (Socket->Wait(ESocketWaitConditions::WaitForRead, WaitTime))
		{
			uint32 Size;

			while (Socket->HasPendingData(Size))
			{
				int32 Read = 0;

				if (Socket->RecvFrom(RecvBuffer.GetData(), RecvBuffer.Num(), Read, *Sender))
				{
					if (Read > 0)
					{
						TSharedPtr<TArray<uint8>, ESPMode::ThreadSafe> ReceivedData = MakeShareable(new TArray<uint8>());
						ReceivedData->SetNumUninitialized(Read);
						memcpy(ReceivedData->GetData(), RecvBuffer.GetData(), Read);
						AsyncTask(ENamedThreads::GameThread, [this, ReceivedData]() { HandleReceivedData(ReceivedData); });
					}
				}
			}
		}
	}
	return 0;
}

void FCborLivelinkSource::HandleReceivedData(TSharedPtr<TArray<uint8>, ESPMode::ThreadSafe> ReceivedData)
{
	auto socket_data = std::vector<uint8_t>(ReceivedData.Get()->GetData(), ReceivedData.Get()->GetData() + ReceivedData->Num());
	auto data_input = json::from_cbor(socket_data);
	// auto data_input = json();
	std::string name = data_input.at("name");
	std::unordered_map<std::string, float> blendshapes = data_input.at("blendshapes");
	std::vector<float> transform = data_input.at("transform");

	UE_LOG(LogTemp, Warning, TEXT("This is the name: %s"), name.c_str());

	UE_LOG(LogTemp, Warning, TEXT("Blendshapes"));
	for (auto& blendshape : blendshapes) {
		UE_LOG(LogTemp, Warning, TEXT("%s - %f"), blendshape.first.c_str(), blendshape.second);
	}
	UE_LOG(LogTemp, Warning, TEXT("Matrix"));
	for (auto& val : transform) {
		UE_LOG(LogTemp, Warning, TEXT("%f "), val);
	}

	bool create_subject = !EncounteredSubjects.Contains(name.c_str());

	FLiveLinkStaticDataStruct StaticDataStruct = FLiveLinkStaticDataStruct(FLiveLinkSkeletonStaticData::StaticStruct());
	FLiveLinkSkeletonStaticData& StaticData = *StaticDataStruct.Cast<FLiveLinkSkeletonStaticData>();
	if (create_subject) {
		StaticData.BoneNames.SetNumUninitialized(1);
		StaticData.BoneParents.SetNumUninitialized(1);
		StaticData.BoneNames[0] = FName(name.c_str());
		StaticData.BoneParents[0] = -1;
		StaticData.PropertyNames.SetNumUninitialized(blendshapes.size());
	}


	FLiveLinkFrameDataStruct FrameDataStruct = FLiveLinkFrameDataStruct(FLiveLinkAnimationFrameData::StaticStruct());
	FLiveLinkAnimationFrameData& FrameData = *FrameDataStruct.Cast<FLiveLinkAnimationFrameData>();
	FrameData.Transforms.SetNumUninitialized(1);

	FVector BoneLocation = FVector(0, 0, 0);
	FQuat BoneRotation = FQuat(0, 0, 0, 1);
	FVector BoneScale = FVector(1, 1, 1);
	FrameData.Transforms[0] = FTransform(BoneRotation, BoneLocation, BoneScale);


	FrameData.PropertyValues.SetNumUninitialized(blendshapes.size());

	int i = 0;
	for (auto& blenshape : blendshapes) {
		if (create_subject) {
			StaticData.PropertyNames[0] = FName(blenshape.first.c_str());
		}
		FrameData.PropertyValues[0] = blenshape.second;
		i++;
	}

	if (create_subject) {
		Client->PushSubjectStaticData_AnyThread({ SourceGuid, name.c_str() }, ULiveLinkAnimationRole::StaticClass(), MoveTemp(StaticDataStruct));
	}
	else {
		EncounteredSubjects.Add(name.c_str());
	}
	Client->PushSubjectFrameData_AnyThread({ SourceGuid, name.c_str()}, MoveTemp(FrameDataStruct));

}



#undef LOCTEXT_NAMESPACE
