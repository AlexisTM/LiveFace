// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.
#include "CborLivelinkSourceFactory.h"
#include "CborLivelinkSource.h"
#include "SCborLivelinkSourceFactory.h"

#define LOCTEXT_NAMESPACE "CborLivelinkSourceFactory"

FText UCborLivelinkSourceFactory::GetSourceDisplayName() const
{
	return LOCTEXT("SourceDisplayName", "JSON LiveLink");
}

FText UCborLivelinkSourceFactory::GetSourceTooltip() const
{
	return LOCTEXT("SourceTooltip", "Creates a connection to a JSON UDP Stream");
}

TSharedPtr<SWidget> UCborLivelinkSourceFactory::BuildCreationPanel(FOnLiveLinkSourceCreated InOnLiveLinkSourceCreated) const
{
	return SNew(SCborLivelinkSourceFactory)
		.OnOkClicked(SCborLivelinkSourceFactory::FOnOkClicked::CreateUObject(this, &UCborLivelinkSourceFactory::OnOkClicked, InOnLiveLinkSourceCreated));
}

TSharedPtr<ILiveLinkSource> UCborLivelinkSourceFactory::CreateSource(const FString& InConnectionString) const
{
	FIPv4Endpoint DeviceEndPoint;
	if (!FIPv4Endpoint::Parse(InConnectionString, DeviceEndPoint))
	{
		return TSharedPtr<ILiveLinkSource>();
	}

	return MakeShared<FCborLivelinkSource>(DeviceEndPoint);
}

void UCborLivelinkSourceFactory::OnOkClicked(FIPv4Endpoint InEndpoint, FOnLiveLinkSourceCreated InOnLiveLinkSourceCreated) const
{
	InOnLiveLinkSourceCreated.ExecuteIfBound(MakeShared<FCborLivelinkSource>(InEndpoint), InEndpoint.ToString());
}

#undef LOCTEXT_NAMESPACE
